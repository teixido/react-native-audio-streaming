require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "TXAudioStreaming"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.description  = <<-DESC
                  TXAudioStreaming
                   DESC
  s.homepage     = package["repository"]['url']
  s.license      = package['license']
  # s.license    = { :type => "MIT", :file => "FILE_LICENSE" }
  s.author       = package['author']
  s.platform     = :ios, "7.0"
  s.source       = { :git => package["repository"]['url'], :tag => "#{s.version}" }

  s.source_files = "ios/**/*.{h,m}"
  s.requires_arc = true

  s.dependency "React"
  #s.dependency "others"

end

